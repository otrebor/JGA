#pragma OPENCL EXTENSION cl_khr_global_int32_base_atomics : enable
#define INF 2147483647

__kernel void
Kernel_Find_Min_Edge_Weight_Per_Vertex(global int* g_graph_nodes_noe,global  int* g_graph_nodes_start,  global int* g_graph_edges,  global int *g_graph_weights,global int* g_graph_MST_edges,global int* g_graph_colorindex,
		global int* g_global_colors,global int* g_active_colors,global int *g_global_min_edge_weight,global int *g_min_edge_weight,
		global int *g_min_edge_index, global int* g_over,global int *g_active_vertices,global int* no_of_nodes)
{
	int tid = get_global_id(0);
	if( tid < no_of_nodes[0] && g_active_vertices[tid]!=0)
	{
		int color, colorindex, minedgeweight, edgeindex, addingvertex, minaddingvertexcolor, addingvertexcolorindex, addingvertexcolor, weight;
		colorindex = g_graph_colorindex[tid];
		color = g_global_colors[colorindex];
		//set the active colors
		if(g_active_colors[color]==0)
			g_active_colors[color]=1;
		
		//The overall termination condition
		                
		if(color!=0)
			g_over[0] = 1;


		int start, end;
		start = g_graph_nodes_start[tid];
		end = g_graph_nodes_noe[tid] + start;

		minedgeweight = INF;
		minaddingvertexcolor = INF;
		edgeindex = INF;
		bool allsame=true;
		
		//Find the Minimum edge for this vertex, which is not in already MST and also not connecting any vertex of same color
		                
		for(int i=start; i< end; i++)
		{
			addingvertex = g_graph_edges[i];
			addingvertexcolorindex = g_graph_colorindex[addingvertex];
			addingvertexcolor = g_global_colors[addingvertexcolorindex];
			weight = g_graph_weights[i];

			if (!g_graph_MST_edges[i] && (color!=addingvertexcolor) )
			{
				if(minedgeweight > weight)
				{
					minedgeweight = weight;
					edgeindex=i;
				}
				if(minedgeweight == weight)
				{
					if(minaddingvertexcolor > addingvertexcolor)
					{
						minaddingvertexcolor = addingvertexcolor;
						edgeindex=i;
					}

				}
			}
			if(color!=addingvertexcolor)
				allsame=false;
		}

		if(allsame)
			g_active_vertices[tid]=0;


		if(edgeindex!=INF)
		{
			//Store these two values for each vertex
			g_min_edge_weight[tid] = minedgeweight;
			g_min_edge_index[tid] = edgeindex;
			//Write the edge weight atomically in a global location
			atomic_min(&g_global_min_edge_weight[color], minedgeweight);
		}


	}

}



__kernel void
Kernel_Find_Min_C2(global int *g_graph_edges,global int *g_global_min_c2,global int* g_graph_colorindex,global int* g_global_colors,global int *g_active_colors,
               global int* g_global_min_edge_weight,global int* g_min_edge_weight,global int* g_min_edge_index,global int* no_of_nodes)
{
        int tid = get_global_id(0);
        if( tid < no_of_nodes[0] )
        {

                int edgeindex = g_min_edge_index[tid];
                if(edgeindex!=INF)
                {
                        int colorindex,color;
                        colorindex = g_graph_colorindex[tid];
                        color = g_global_colors[colorindex];

                        if(g_global_min_edge_weight[color] ==  g_min_edge_weight[tid]) //The weight is found, write the index value
                        {
                                int v2 = g_graph_edges[edgeindex];
                                int v2_index = g_graph_colorindex[v2];
                                int c2 = g_global_colors[v2_index];
                                atomic_min(&g_global_min_c2[color],c2);
                        }
                        else
                        {
                                g_min_edge_weight[tid]= INF;
                                g_min_edge_index[tid] = INF;
                        }
                }

        }
}



__kernel void
Kernel_Add_Edge_To_Global_Min(global int * g_graph_edges,global int * g_global_min_c2,global int* g_graph_colorindex,global int* g_global_colors,
       global int* g_global_min_edge_index,global int* g_min_edge_weight,global int* g_min_edge_index,global int* no_of_nodes)
{
        int tid = get_global_id(0);
        if( tid < no_of_nodes[0] )
        {
                int colorindex,color;
                colorindex = g_graph_colorindex[tid];
                color = g_global_colors[colorindex];
                int edgeindex = g_min_edge_index[tid];

                if(edgeindex!=INF )//The weight is found, write the index value
                {
                        int v2 = g_graph_edges[edgeindex];
                        int v2i = g_graph_colorindex[v2];
                        int c2 = g_global_colors[v2i];
                        if(c2 == g_global_min_c2[color])
                                {
                                atomic_min(&g_global_min_edge_index[color], edgeindex);
                                }
                        else
                                {
                                g_min_edge_weight[tid]=INF;
                                g_min_edge_index[tid]=INF;
                                }
                }

        }
}



__kernel void
Kernel_Find_Degree(global int * g_graph_edges,global int* g_graph_colorindex,global int* g_global_colors,global int* g_global_min_edge_index,
                global int* g_degree, global int * g_updating_degree, global int* g_prev_colors,global int* g_prev_colorindex,global int* no_of_nodes)
{
        int color = get_global_id(0);
        if( color < no_of_nodes[0] )
        {
                int edgeindex = g_global_min_edge_index[color];
                if(edgeindex!=INF )
                {
                        int v2 = g_graph_edges[edgeindex];
                        int civ2 = g_graph_colorindex[v2];
                        int colv1 = color;
                        int colv2 = g_global_colors[civ2];


                        atomic_inc(&g_degree[colv1]);
                        atomic_inc(&g_degree[colv2]);

                        atomic_inc(&g_updating_degree[colv1]);
                        atomic_inc(&g_updating_degree[colv2]);
                }

                g_prev_colors[color] = g_global_colors[color];
                g_prev_colorindex[color] = g_graph_colorindex[color];
        }
}

        __kernel void
Kernel_Prop_Colors1(global int *g_graph_edges,global int *g_graph_colorindex, global int *g_global_colors, global int* g_global_min_edge_index,
        global int* g_updating_global_colors, global int* no_of_nodes)
{

        int tid = get_global_id(0);
        if(tid < no_of_nodes[0])
        {
                int edgeindex = g_global_min_edge_index[tid];
                if(edgeindex!=INF)
                {
                        //Update the color of each neighbour using edges present in newly added edges MST only
                        int v1color,v2,v2index,v2color;
                        v2 = g_graph_edges[edgeindex];
                        v1color = g_global_colors[tid];
                        v2index = g_graph_colorindex[v2];
                        v2color = g_global_colors[v2index];
                        atomic_min( &g_updating_global_colors[tid], v2color);
                        atomic_min( &g_updating_global_colors[v2index], v1color);
                }
        }
}
			

 __kernel void
Kernel_Prop_Colors2(global int *g_global_colors, global int *g_active_colors, global int *g_updating_global_colors,  global int* g_done, global int* no_of_nodes)
{
        //This kernel works on the global_colors[] array
        int tid = get_global_id(0);
        if(tid < no_of_nodes[0])
        {
                if(g_active_colors[tid])
                {
                        int color = g_global_colors[tid];
                        int updatingcolor = g_updating_global_colors[tid];
                        if(color > updatingcolor)
                        {
                                g_global_colors[tid] = updatingcolor;
                                *g_done = 1; //Termination condition for Kernel 4 and Kernel 5 while loop
                        }
                }
                g_updating_global_colors[tid] = g_global_colors[tid];
        }
}


__kernel void
Kernel_Dec_Degree1(global int *g_active_colors,  global int* g_degree, global int* g_global_min_edge_index, global int* g_graph_edges,
                global int* g_prev_colorindex, global int* g_prev_colors,  global int* g_updating_degree, global int* g_graph_MST_edges ,global int* no_of_nodes)
{
        //This kernel works on the global_colors[] array  
        int tid = get_global_id(0);
        if(tid < no_of_nodes[0])
        {
                int edgeindex = g_global_min_edge_index[tid];
                if(edgeindex != INF)
                {
                        int v2 = g_graph_edges[edgeindex];
                        int ci_v2 = g_prev_colorindex[v2];
                        int colv1 = tid;
                        int colv2 = g_prev_colors[ci_v2];

                        if(g_degree[colv1]==1)
                        {
                                atomic_dec(&g_updating_degree[colv1]);
                                atomic_dec(&g_updating_degree[colv2]);

                                //Here Only Mark The edge into MST
                                g_graph_MST_edges[edgeindex] = 1;

                                //Remove this Edge, its work is done      
                                g_global_min_edge_index[tid] = INF;
                        }
                }
        }
}

__kernel void
Kernel_Dec_Degree2( global int *g_degree, global int *g_active_colors,  global int *g_updating_degree, global int *g_removing,global int* no_of_nodes)
{
        //This kernel works on the global_colors[] array  
        int color = get_global_id(0);
        if(color < no_of_nodes[0])
        {
                if(g_active_colors[color])
                {
                        int updating_degree = g_updating_degree[color];
                        if(g_degree[color] > updating_degree)
                        {
                                g_degree[color] = updating_degree;
                                *g_removing = 1;
                        }
                }
        }
}


        __kernel void
Kernel_Add_Remaining_Edges(global int *g_global_min_edge_index,  global int* g_graph_MST_edges, global int* no_of_nodes)
{
        //This kernel works on the global_colors[] array  
        int tid = get_global_id(0);
        if(tid < no_of_nodes[0])
        {
                int edgeindex = g_global_min_edge_index[tid];
                if(edgeindex!=INF)
                {
                        g_graph_MST_edges[edgeindex]=1;
                }
        }

}


__kernel void
Kernel_Edge_Per_Cycle_New_Color(global int *g_global_min_edge_index,global int* g_graph_edges, global int* g_graph_colorindex,
                                global int *g_global_colors, global int *g_cycle_edge, global int* no_of_nodes)
{
        int tid = get_global_id(0);
        if(tid < no_of_nodes[0])
        {
                int edgeindex = g_global_min_edge_index[tid];
                if(edgeindex!=INF)
                        {
                        int v2 = g_graph_edges[edgeindex];
                        int ci_v2 = g_graph_colorindex[v2];
                        int colv2 = g_global_colors[ci_v2];
                        atomic_min(&g_cycle_edge[colv2], edgeindex);
                        }

        }
}

__kernel void
Kernel_Remove_Cycle_Edge_From_MST(global int *g_cycle_edge, global int *g_graph_MST_edges, global int* no_of_nodes)
{
        int tid = get_global_id(0);
        if(tid < no_of_nodes[0])
        {
                int edgeindex = g_cycle_edge[tid];
                if(edgeindex!=INF)
                        {
                        g_graph_MST_edges[edgeindex] = 0;
                        }

        }
}

__kernel void
Kernel_Update_Colorindex(global int *g_graph_colorindex, global int *g_global_colors, global int* no_of_nodes)
{
        int tid = get_global_id(0);
        if(tid < no_of_nodes[0] )
        {
                int colorindex = g_graph_colorindex[tid];
                int color = g_global_colors[colorindex];
                while(color!=colorindex)
                {
                        colorindex = g_global_colors[color];
                        color = g_global_colors[colorindex];
                }

                //This is the color I should point to
                g_graph_colorindex[tid]=colorindex;

        }
}

__kernel void
Kernel_Reinitialize(global int *g_active_colors, global int *g_global_min_edge_index, global int *g_global_min_c2,
                        global int* g_global_min_edge_weight, global int* g_min_edge_index, global int* g_min_edge_weight,
                         global int* g_degree,  global int* g_updating_degree,
                        global int* g_prev_colors, global int *g_cycle_edge, global int* g_prev_colorindex, global int* no_of_nodes)
{
        int tid = get_global_id(0);
        if(tid < no_of_nodes[0] )
        {
                //Re-Initialization of arrays
                g_active_colors[tid] = 0;
                g_global_min_edge_index[tid] = INF;
                g_global_min_edge_weight[tid] = INF;
                g_global_min_c2[tid] = INF;
                g_min_edge_index[tid]=INF;
                g_min_edge_weight[tid]=INF;
                g_degree[tid]=0;
                g_updating_degree[tid]=0;
                g_prev_colors[tid]=INF;
                g_prev_colorindex[tid]=INF;
                g_cycle_edge[tid]=INF;
        }
}


